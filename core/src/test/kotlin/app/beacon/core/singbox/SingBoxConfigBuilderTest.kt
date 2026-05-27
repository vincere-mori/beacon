package app.beacon.core.singbox

import app.beacon.core.model.DnsMode
import app.beacon.core.parser.ProfileInputParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SingBoxConfigBuilderTest {
    private val parser = ProfileInputParser(clock = { 42L })
    private val builder = SingBoxConfigBuilder()
    private val json = Json.parseToJsonElement(
        builder.build(
            parser.parse(
                "vless://11111111-1111-1111-1111-111111111111@example.com:443" +
                    "?security=reality&sni=apple.com&fp=chrome&pbk=pubkey&sid=abcd#Main"
            ),
            SingBoxConfigSettings(dnsMode = DnsMode.Google, ipv6Enabled = true)
        )
    ).jsonObject

    @Test
    fun buildsTunInbound() {
        val inbound = json["inbounds"]!!.jsonArray.first().jsonObject

        assertEquals("tun", inbound["type"]!!.jsonPrimitive.content)
        assertEquals("mixed", inbound["stack"]!!.jsonPrimitive.content)
        assertTrue(inbound["address"]!!.jsonArray.size == 2)
        assertEquals(true, inbound["endpoint_independent_nat"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("5m", inbound["udp_timeout"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildsMixedInbound() {
        val profile = parser.parse(
            "vless://11111111-1111-1111-1111-111111111111@example.com:443" +
                "?security=reality&sni=apple.com&pbk=pubkey#Main"
        )
        val mixedJson = Json.parseToJsonElement(
            builder.build(profile, SingBoxConfigSettings(inboundMode = InboundMode.Mixed))
        ).jsonObject
        val inbound = mixedJson["inbounds"]!!.jsonArray.first().jsonObject

        assertEquals("mixed", inbound["type"]!!.jsonPrimitive.content)
        assertEquals("127.0.0.1", inbound["listen"]!!.jsonPrimitive.content)
        assertEquals("2080", inbound["listen_port"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildsVlessRealityOutbound() {
        val outbound = json["outbounds"]!!.jsonArray.first().jsonObject
        val tls = outbound["tls"]!!.jsonObject
        val reality = tls["reality"]!!.jsonObject

        assertEquals("vless", outbound["type"]!!.jsonPrimitive.content)
        assertEquals("example.com", outbound["server"]!!.jsonPrimitive.content)
        assertEquals("bootstrap", outbound["domain_resolver"]!!.jsonPrimitive.content)
        assertEquals("apple.com", tls["server_name"]!!.jsonPrimitive.content)
        assertEquals("pubkey", reality["public_key"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildsWarpEndpointAndRoutesGemini() {
        val profile = parser.parse(
            "vless://11111111-1111-1111-1111-111111111111@example.com:443" +
                "?security=reality&sni=apple.com&pbk=pubkey#Main"
        )
        val warpJson = Json.parseToJsonElement(
            builder.build(
                profile,
                SingBoxConfigSettings(
                    inboundMode = InboundMode.Mixed,
                    warpEnabled = true,
                    warpPrivateKey = "private",
                    warpLocalAddressV4 = "172.16.0.2",
                    warpPeerPublicKey = "peer",
                    warpEndpoint = "162.159.192.1:2408",
                    warpReserved = listOf(1, 2, 3)
                )
            )
        ).jsonObject
        val endpoint = warpJson["endpoints"]!!.jsonArray.first().jsonObject
        val peer = endpoint["peers"]!!.jsonArray.first().jsonObject
        val warpRule = warpJson["route"]!!.jsonObject["rules"]!!.jsonArray[4].jsonObject

        assertEquals("wireguard", endpoint["type"]!!.jsonPrimitive.content)
        assertEquals("proxy", endpoint["detour"]!!.jsonPrimitive.content)
        assertEquals("162.159.192.1", peer["address"]!!.jsonPrimitive.content)
        assertEquals(listOf(1, 2, 3), peer["reserved"]!!.jsonArray.map { it.jsonPrimitive.int })
        assertEquals("warp", warpRule["outbound"]!!.jsonPrimitive.content)
    }

    @Test
    fun routesPrivateNetworkDirectly() {
        val route = json["route"]!!.jsonObject
        val privateRule = route["rules"]!!.jsonArray[2].jsonObject

        assertEquals("route", privateRule["action"]!!.jsonPrimitive.content)
        assertEquals("direct", privateRule["outbound"]!!.jsonPrimitive.content)
        assertEquals("remote", route["default_domain_resolver"]!!.jsonPrimitive.content)
        assertEquals("proxy", route["final"]!!.jsonPrimitive.content)
    }

    @Test
    fun usesRouteActionsForSniffAndDns() {
        val rules = json["route"]!!.jsonObject["rules"]!!.jsonArray

        assertEquals("sniff", rules[0].jsonObject["action"]!!.jsonPrimitive.content)
        assertEquals("hijack-dns", rules[1].jsonObject["action"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildsNewDnsServerFormat() {
        val servers = json["dns"]!!.jsonObject["servers"]!!.jsonArray
        val bootstrap = servers[0].jsonObject
        val remote = servers[1].jsonObject

        assertEquals("udp", bootstrap["type"]!!.jsonPrimitive.content)
        assertEquals("1.1.1.1", bootstrap["server"]!!.jsonPrimitive.content)
        assertNull(bootstrap["detour"])
        assertEquals("https", remote["type"]!!.jsonPrimitive.content)
        assertEquals("dns.google", remote["server"]!!.jsonPrimitive.content)
        assertEquals("bootstrap", remote["domain_resolver"]!!.jsonPrimitive.content)
    }
}
