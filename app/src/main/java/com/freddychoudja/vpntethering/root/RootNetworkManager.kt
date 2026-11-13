package com.freddychoudja.vpntethering.root

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Détection d'interfaces et application / nettoyage des règles iptables
 * pour router le trafic Hotspot vers l'interface VPN (root requis).
 */
class RootNetworkManager {

    data class Interfaces(
        val hotspot: String,
        val vpn: String
    )

    data class ApplyResult(
        val success: Boolean,
        val message: String,
        val interfaces: Interfaces? = null
    )

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        Shell.isAppGrantedRoot() == true || Shell.getShell().isRoot
    }

    suspend fun detectInterfaces(): Interfaces? = withContext(Dispatchers.IO) {
        val hotspot = detectHotspotInterface() ?: return@withContext null
        val vpn = detectVpnInterface() ?: return@withContext null
        Interfaces(hotspot = hotspot, vpn = vpn)
    }

    suspend fun enableTethering(): ApplyResult = withContext(Dispatchers.IO) {
        if (!ensureRoot()) {
            return@withContext ApplyResult(false, "Accès root indisponible")
        }

        val interfaces = detectInterfaces()
            ?: return@withContext ApplyResult(
                false,
                "Interfaces introuvables. Activez le Hotspot et le VPN (tun0)."
            )

        val hotspot = interfaces.hotspot
        val vpn = interfaces.vpn

        // Mémorise les interfaces pour un nettoyage fiable
        activeHotspot = hotspot
        activeVpn = vpn

        val commands = listOf(
            "echo 1 > /proc/sys/net/ipv4/ip_forward",
            // rp_filter souple pour éviter le drop asymétrique via VPN
            "echo 2 > /proc/sys/net/ipv4/conf/all/rp_filter || true",
            "echo 2 > /proc/sys/net/ipv4/conf/$hotspot/rp_filter || true",
            "echo 2 > /proc/sys/net/ipv4/conf/$vpn/rp_filter || true",
            // Règles FORWARD (insertion en tête pour prioriser)
            "iptables -I FORWARD 1 -i $hotspot -o $vpn -j ACCEPT",
            "iptables -I FORWARD 1 -i $vpn -o $hotspot -m state --state RELATED,ESTABLISHED -j ACCEPT",
            // Masquerading NAT vers le VPN
            "iptables -t nat -I POSTROUTING 1 -o $vpn -j MASQUERADE"
        )

        val result = Shell.cmd(*commands.toTypedArray()).exec()
        if (result.isSuccess) {
            ApplyResult(
                success = true,
                message = "Actif : $hotspot → $vpn",
                interfaces = interfaces
            )
        } else {
            // Tentative de rollback partiel
            disableTetheringInternal(hotspot, vpn)
            ApplyResult(
                success = false,
                message = "Échec iptables : ${result.err.joinToString(" ").ifBlank { "erreur inconnue" }}"
            )
        }
    }

    suspend fun disableTethering(): ApplyResult = withContext(Dispatchers.IO) {
        if (!ensureRoot()) {
            return@withContext ApplyResult(false, "Accès root indisponible")
        }

        val hotspot = activeHotspot ?: detectHotspotInterface()
        val vpn = activeVpn ?: detectVpnInterface()

        if (hotspot == null || vpn == null) {
            activeHotspot = null
            activeVpn = null
            return@withContext ApplyResult(
                true,
                "Aucune règle active à nettoyer (interfaces absentes)"
            )
        }

        disableTetheringInternal(hotspot, vpn)
        activeHotspot = null
        activeVpn = null
        ApplyResult(true, "Désactivé — règles iptables nettoyées")
    }

    private fun disableTetheringInternal(hotspot: String, vpn: String) {
        // -D est idempotent côté intention : on ignore les échecs si la règle n'existe plus
        val commands = listOf(
            "iptables -D FORWARD -i $hotspot -o $vpn -j ACCEPT || true",
            "iptables -D FORWARD -i $vpn -o $hotspot -m state --state RELATED,ESTABLISHED -j ACCEPT || true",
            "iptables -t nat -D POSTROUTING -o $vpn -j MASQUERADE || true"
        )
        Shell.cmd(*commands.toTypedArray()).exec()
    }

    private fun ensureRoot(): Boolean {
        val shell = Shell.getShell()
        return shell.isRoot
    }

    private fun detectHotspotInterface(): String? {
        // Ordre de préférence selon les OEM / versions Android
        val candidates = listOf("ap0", "swlan0", "wlan1", "wlan0", "softap0", "uap0")
        val upInterfaces = listUpInterfaces()
        return candidates.firstOrNull { it in upInterfaces && looksLikeHotspot(it) }
            ?: upInterfaces.firstOrNull { iface ->
                candidates.contains(iface) || iface.startsWith("ap") || iface.startsWith("swlan")
            }
    }

    private fun detectVpnInterface(): String? {
        val upInterfaces = listUpInterfaces()
        // tun0 le plus courant ; certains clients utilisent tun1, orbot, etc.
        return listOf("tun0", "tun1", "ppp0").firstOrNull { it in upInterfaces }
            ?: upInterfaces.firstOrNull { it.startsWith("tun") }
    }

    private fun listUpInterfaces(): Set<String> {
        val result = Shell.cmd(
            "ip -o link show up | awk -F': ' '{print \$2}' | cut -d'@' -f1"
        ).exec()
        return result.out
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "lo" }
            .toSet()
    }

    private fun looksLikeHotspot(iface: String): Boolean {
        // Heuristique : interface Wi-Fi up avec une adresse privée typique hotspot
        val addr = Shell.cmd("ip -o -4 addr show $iface | awk '{print \$4}'").exec()
        val cidr = addr.out.firstOrNull().orEmpty()
        return cidr.startsWith("192.168.") ||
            cidr.startsWith("172.") ||
            iface == "ap0" ||
            iface == "swlan0" ||
            iface.startsWith("softap")
    }

    companion object {
        @Volatile
        private var activeHotspot: String? = null

        @Volatile
        private var activeVpn: String? = null
    }
}
