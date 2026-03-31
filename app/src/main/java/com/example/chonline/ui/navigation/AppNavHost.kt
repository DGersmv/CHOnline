package com.example.chonline.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.chonline.call.CallCommand
import com.example.chonline.call.CallCoordinator
import com.example.chonline.di.AppContainer
import com.example.chonline.ui.AppViewModelFactory
import com.example.chonline.ui.auth.AuthViewModel
import com.example.chonline.ui.auth.LoginScreen
import com.example.chonline.ui.auth.VerifyScreen
import com.example.chonline.ui.admin.AdminClientsScreen
import com.example.chonline.ui.chat.ChatScreen
import com.example.chonline.ui.call.CallScreen
import com.example.chonline.ui.call.CallViewModel
import com.example.chonline.ui.call.CallViewModelFactory
import com.example.chonline.ui.main.MainShellScreen
import com.example.chonline.ui.rooms.GroupCreateScreen
import com.example.chonline.ui.rooms.GroupEditScreen
import com.example.chonline.ui.profile.ProfileScreen
import com.example.chonline.ui.profile.ProfileViewModel
import com.example.chonline.ui.rooms.RoomsViewModel
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import ru.rustore.sdk.pushclient.RuStorePushClient

@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val session by container.tokenStore.session.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(session) {
        if (session != null) {
            container.chatRepository.connectSocket()
            kotlin.runCatching {
                RuStorePushClient.getToken()
                    .addOnSuccessListener { token ->
                        scope.launch { container.authRepository.registerPushToken(token) }
                    }
            }
        } else {
            container.chatRepository.disconnectSocket()
        }
    }

    LaunchedEffect(Unit) {
        container.chatRepository.socketEvents.collect { ev ->
            if (ev is com.example.chonline.data.socket.SocketEvent.CallInvite) {
                CallCoordinator.submit(
                    CallCommand.IncomingInvite(
                        com.example.chonline.call.CallInvite(
                            callId = ev.callId,
                            roomId = ev.roomId,
                            fromUserId = ev.fromUserId,
                            fromName = ev.fromName,
                            mode = ev.mode,
                            ts = ev.ts,
                        ),
                    ),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        CallCoordinator.commands.collect { cmd ->
            when (cmd) {
                is CallCommand.IncomingInvite -> {
                    val i = cmd.invite
                    val route = "call/${url(i.callId)}/${url(i.roomId)}/${url(i.fromUserId)}/${url(i.fromName)}/1/0"
                    nav.navigate(route) { launchSingleTop = true }
                }
                is CallCommand.Accept -> {
                    val i = cmd.invite
                    val route = "call/${url(i.callId)}/${url(i.roomId)}/${url(i.fromUserId)}/${url(i.fromName)}/1/1"
                    nav.navigate(route) { launchSingleTop = true }
                }
                is CallCommand.Decline -> {
                    container.chatRepository.rejectCall(cmd.invite.callId)
                }
            }
        }
    }

    val factory = remember(container) { AppViewModelFactory(container) }

    NavHost(navController = nav, startDestination = "start") {
        composable("start") {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            LaunchedEffect(Unit) {
                val s = container.tokenStore.session.value
                if (s == null) {
                    nav.navigate("login") {
                        popUpTo("start") { inclusive = true }
                        launchSingleTop = true
                    }
                    return@LaunchedEffect
                }
                val snap = container.authRepository.loadProfileForStartup().getOrNull()
                val needProfile = snap == null || snap.name.isBlank() || snap.phone.isBlank()
                if (needProfile) {
                    nav.navigate("profile") {
                        popUpTo("start") { inclusive = true }
                        launchSingleTop = true
                    }
                } else {
                    nav.navigate("rooms") {
                        popUpTo("start") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
        composable("login") {
            val vm: AuthViewModel = viewModel(factory = factory)
            val authUi by vm.ui.collectAsStateWithLifecycle()
            LaunchedEffect(authUi.loggedIn) {
                if (!authUi.loggedIn) return@LaunchedEffect
                vm.consumeLoggedIn()
                nav.navigate("start") {
                    popUpTo("login") { inclusive = true }
                    launchSingleTop = true
                }
            }
            LoginScreen(
                viewModel = vm,
                onCodeSent = { email ->
                    val enc = URLEncoder.encode(email, StandardCharsets.UTF_8.name())
                    nav.navigate("verify/$enc") { launchSingleTop = true }
                },
            )
        }
        composable(
            route = "verify/{emailEnc}",
            arguments = listOf(navArgument("emailEnc") { type = NavType.StringType }),
        ) { entry ->
            val enc = entry.arguments?.getString("emailEnc")!!
            val email = URLDecoder.decode(enc, StandardCharsets.UTF_8.name())
            val vm: AuthViewModel = viewModel(factory = factory)
            VerifyScreen(
                email = email,
                viewModel = vm,
                onSuccess = {
                    nav.navigate("start") {
                        popUpTo("login") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable("profile") {
            val vm: ProfileViewModel = viewModel(factory = factory)
            ProfileScreen(
                viewModel = vm,
                onDone = {
                    nav.navigate("rooms") {
                        popUpTo("profile") { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable("rooms") {
            val roomsVm: RoomsViewModel = viewModel(factory = factory)
            val profileVm: ProfileViewModel = viewModel(factory = factory)
            MainShellScreen(
                roomsViewModel = roomsVm,
                profileViewModel = profileVm,
                container = container,
                onOpenChat = { roomId ->
                    val e = URLEncoder.encode(roomId, StandardCharsets.UTF_8.name())
                    nav.navigate("chat/$e")
                },
                onCreateGroup = {
                    nav.navigate("group-create") { launchSingleTop = true }
                },
                onOpenAdminClients = {
                    nav.navigate("admin-clients") { launchSingleTop = true }
                },
                onLogout = {
                    scope.launch {
                        container.authRepository.logout()
                        container.chatRepository.disconnectSocket()
                        nav.navigate("login") {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
        composable("admin-clients") {
            AdminClientsScreen(
                container = container,
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = "chat/{roomIdEnc}",
            arguments = listOf(navArgument("roomIdEnc") { type = NavType.StringType }),
        ) { entry ->
            val enc = entry.arguments?.getString("roomIdEnc")!!
            val roomId = URLDecoder.decode(enc, StandardCharsets.UTF_8.name())
            ChatScreen(
                roomId = roomId,
                container = container,
                onBack = { nav.popBackStack() },
                onStartCall = { peerId, peerName ->
                    val route = "call/${url("")}/${url(roomId)}/${url(peerId)}/${url(peerName)}/0/0"
                    nav.navigate(route) { launchSingleTop = true }
                },
                onEditGroup = {
                    val e = URLEncoder.encode(roomId, StandardCharsets.UTF_8.name())
                    nav.navigate("group-edit/$e")
                },
                onOpenGroupAsMember = {
                    val e = URLEncoder.encode(roomId, StandardCharsets.UTF_8.name())
                    nav.navigate("group-edit/$e")
                },
            )
        }
        composable(
            route = "call/{callIdEnc}/{roomIdEnc}/{peerIdEnc}/{peerNameEnc}/{incoming}/{autoAccept}",
            arguments = listOf(
                navArgument("callIdEnc") { type = NavType.StringType },
                navArgument("roomIdEnc") { type = NavType.StringType },
                navArgument("peerIdEnc") { type = NavType.StringType },
                navArgument("peerNameEnc") { type = NavType.StringType },
                navArgument("incoming") { type = NavType.IntType },
                navArgument("autoAccept") { type = NavType.IntType },
            ),
        ) { entry ->
            val callId = URLDecoder.decode(entry.arguments?.getString("callIdEnc")!!, StandardCharsets.UTF_8.name())
            val roomId = URLDecoder.decode(entry.arguments?.getString("roomIdEnc")!!, StandardCharsets.UTF_8.name())
            val peerId = URLDecoder.decode(entry.arguments?.getString("peerIdEnc")!!, StandardCharsets.UTF_8.name())
            val peerName = URLDecoder.decode(entry.arguments?.getString("peerNameEnc")!!, StandardCharsets.UTF_8.name())
            val incoming = (entry.arguments?.getInt("incoming") ?: 0) == 1
            val autoAccept = (entry.arguments?.getInt("autoAccept") ?: 0) == 1
            val vm: CallViewModel = viewModel(
                key = "call-${callId}-${peerId}-${incoming}",
                factory = CallViewModelFactory(
                    appContext = nav.context.applicationContext,
                    repo = container.chatRepository,
                    callId = callId,
                    roomId = roomId,
                    peerId = peerId,
                    peerName = peerName,
                    incoming = incoming,
                    autoAccept = autoAccept,
                ),
            )
            CallScreen(
                viewModel = vm,
                onClose = { nav.popBackStack() },
            )
        }
        composable("group-create") {
            val roomsVm: RoomsViewModel = viewModel(factory = factory)
            GroupCreateScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onCreated = { roomId ->
                    roomsVm.refresh()
                    val e = URLEncoder.encode(roomId, StandardCharsets.UTF_8.name())
                    nav.navigate("chat/$e") {
                        popUpTo("group-create") { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = "group-edit/{roomIdEnc}",
            arguments = listOf(navArgument("roomIdEnc") { type = NavType.StringType }),
        ) { entry ->
            val enc = entry.arguments?.getString("roomIdEnc")!!
            val roomId = URLDecoder.decode(enc, StandardCharsets.UTF_8.name())
            GroupEditScreen(
                roomId = roomId,
                container = container,
                onBack = { nav.popBackStack() },
                onRoomRemoved = {
                    nav.popBackStack("rooms", inclusive = false)
                },
            )
        }
    }
}

private fun url(v: String): String = URLEncoder.encode(v, StandardCharsets.UTF_8.name())
