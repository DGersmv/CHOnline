package com.example.chonline.call



import com.example.chonline.BuildConfig

import org.webrtc.PeerConnection



/** ICE: STUN + TURN. Свой coturn — ключи ice.turn.* в local.properties (см. app/build.gradle.kts). Иначе Metered. */

object WebRtcIceServers {

    fun peerConnectionIceServers(): List<PeerConnection.IceServer> {

        val stunA = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()

        val stunB = PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()



        val customUrls = BuildConfig.ICE_TURN_URLS.trim()

        val customUser = BuildConfig.ICE_TURN_USERNAME.trim()

        val customPass = BuildConfig.ICE_TURN_PASSWORD.trim()

        if (customUrls.isNotEmpty() && customUser.isNotEmpty() && customPass.isNotEmpty()) {

            val urls = customUrls.split(',').map { it.trim() }.filter { it.isNotEmpty() }

            if (urls.isNotEmpty()) {

                val turn =

                    PeerConnection.IceServer.builder(urls)

                        .setUsername(customUser)

                        .setPassword(customPass)

                        .createIceServer()

                return listOf(stunA, stunB, turn)

            }

        }



        val turnPublic =

            PeerConnection.IceServer.builder(

                listOf(

                    "turn:openrelay.metered.ca:80?transport=udp",

                    "turn:openrelay.metered.ca:443?transport=tcp",

                ),

            )

                .setUsername("openrelayproject")

                .setPassword("openrelayproject")

                .createIceServer()

        return listOf(stunA, stunB, turnPublic)

    }

}


