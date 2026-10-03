package com.nahuel.homeflow

import com.nahuel.homeflow.devices.Http
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpTest {
    @Test fun lanHostsArePrivate() {
        listOf("192.168.178.20", "10.0.0.5", "172.16.1.1", "172.31.255.255", "127.0.0.1",
            "169.254.3.4", "100.101.102.103", "[fe80::1]", "fd00::5", "hue-bridge",
            "shelly.local", "nas.fritz.box", "phone.tail1234.ts.net")
            .forEach { assertTrue(it, Http.isPrivateHost(it)) }
    }

    @Test fun internetHostsAreNotPrivate() {
        listOf("8.8.8.8", "172.32.0.1", "100.128.0.1", "api.spotify.com", "maker.ifttt.com", "", "2001:db8::1")
            .forEach { assertFalse(it, Http.isPrivateHost(it)) }
    }
}
