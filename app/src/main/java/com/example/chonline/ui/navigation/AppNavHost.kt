package com.example.chonline.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import android.content.Intent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.chonline.call.CallCommand
import com.example.chonline.call.CallCoordinator
import com.example.chonline.call.CallInvite
import com.example.chonline.call.CallNotificationParser
import com.example.chonline.call.IncomingCallNotifier
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
import com.example.chonline.ui.navigation.NotificationNavigationCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.example.chonline.push.PushMessageNotifier
import com.example.chonline.push.PushTokenRegistrar

@Composable
fun AppNavHost(
    container: AppContainer,
    launchIntentKey: Int,
) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val session by container.tokenStore.session.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    LaunchedEffect(session) {
        if (session != null) {
            container.chatRepository.connectSocket()
            kotlin.runCatching {
                PushTokenRegistrar.registerWithServer(container, scope)
            }
        } else {
            container.chatRepository.disconnectSocket()
        }
    }

    // Сокет до navigate: иначе call:accept/offer теряются, UI зависает на «Подключение».
    LaunchedEffect(launchIntentKey, session) {
        if (session == null) return@LaunchedEffect
        val act = context as? ComponentActivity ?: return@LaunchedEffect
        val intent = act.intent
        val needsSocketFirst =
            intent?.action == PushMessageNotifier.ACTION_OPEN_MESSAGE ||
                intent?.hasExtra(CallNotificationParser.EXTRA_CALL_ID) == true ||
                intent?.hasExtra(CallNotificationParser.EXTRA_INVITE_PAYLOAD) == true
        if (needsSocketFirst) {
            container.chatRepository.connectSocket()
            container.chatRepository.awaitSocketConnected(25_000)
        }
        processMainActivityIntentForNavigationSuspend(act, nav, container)
    }

    DisposableEffect(nav) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, arguments ->
            when (destination.route?.substringBefore("/")) {
                "chat" -> {
                    val roomEnc = arguments?.getString("roomIdEnc")
                    val roomId = roomEnc?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
                    AppRuntimeState.setActiveChat(roomId)
                    AppRuntimeState.setActiveCall(null)
                }
                "call" -> {
                    val callEnc = arguments?.getString("callIdEnc")
                    val callId = callEnc?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
                    AppRuntimeState.setActiveCall(callId)
                    AppRuntimeState.setActiveChat(null)
                }
                else -> {
                    AppRuntimeState.setActiveChat(null)
                    AppRuntimeState.setActiveCall(null)
                }
            }
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose {
            nav.removeOnDestinationChangedListener(listener)
            AppRuntimeState.setActiveChat(null)
            AppRuntimeState.setActiveCall(null)
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
                    IncomingCallNotifier.cancel(nav.context.applicationContext, i.callId)
                    val route = "call/${url(i.callId)}/${url(i.roomId)}/${url(i.fromUserId)}/${url(i.fromName)}/1/1"
                    nav.navigate(route) { launchSingleTop = true }
                }
                is CallCommand.Decline -> {
                    IncomingCallNotifier.cancel(nav.context.applicationContext, cmd.invite.callId)
                    container.chatRepository.rejectCall(cmd.invite.callId)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        container.chatRepository.socketEvents.collect { ev ->
            if (ev is com.example.chonline.data.socket.SocketEvent.CallInvite) {
                val invite = com.example.chonline.call.CallInvite(
                    callId = ev.callId,
                    roomId = ev.roomId,
                    fromUserId = ev.fromUserId,
                    fromName = ev.fromName,
                    mode = ev.mode,
                    ts = ev.ts,
                )
                val foreground = AppRuntimeState.isForeground.value
                val activeCall = AppRuntimeState.activeCallId.value
                if (foreground && activeCall.isNullOrBlank()) {
                    CallCoordinator.submit(CallCommand.IncomingInvite(invite))
                } else if (!foreground && activeCall.isNullOrBlank()) {
                    IncomingCallNotifier.show(nav.context.applicationContext, invite)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        NotificationNavigationCoordinator.consumePendingOpenChat()?.let { cmd ->
            val e = URLEncoder.encode(cmd.roomId, StandardCharsets.UTF_8.name())
            nav.navigate("chat/$e") { launchSingleTop = true }
        }
        NotificationNavigationCoordinator.openChat.collect { cmd ->
            val e = URLEncoder.encode(cmd.roomId, StandardCharsets.UTF_8.name())
            nav.navigate("chat/$e") { launchSingleTop = true }
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
                repeat(40) {
                    val r = nav.currentDestination?.route.orEmpty()
                    if (r.startsWith("call/")) return@LaunchedEffect
                    delay(16)
                }
                val route = nav.currentDestination?.route.orEmpty()
                if (route.startsWith("call/")) return@LaunchedEffect
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
            /** Один VM на callId: маршрут 1/0 и 1/1 отличается только autoAccept — второй экземпляр терял offer/ICE из буфера. */
            val vm: CallViewModel = viewModel(
                key = "call-$callId",
                factory = CallViewModelFactory(
                    appContext = nav.context.applicationContext,
                    repo = container.chatRepository,
                    callId = callId,
                    roomId = roomId,
                    peerId = peerId,
                    peerName = peerName,
                    incoming = incoming,
                ),
            )
            LaunchedEffect(incoming, autoAccept) {
                if (incoming) {
                    vm.syncIncomingRouteAutoAccept(autoAccept)
                }
            }
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

private suspend fun enrichInviteFromSocketIfNeeded(
    container: AppContainer,
    base: CallInvite,
): CallInvite {
    if (base.fromUserId.isNotBlank() && base.roomId.isNotBlank()) return base
    val sk = container.chatRepository.awaitCallInviteForCallId(base.callId, 1500)
    if (sk == null) {
        return base
    }
    return base.copy(
        roomId = base.roomId.ifBlank { sk.roomId },
        fromUserId = base.fromUserId.ifBlank { sk.fromUserId },
        fromName = base.fromName.ifBlank { sk.fromName },
        mode = base.mode.ifBlank { sk.mode },
    )
}

private suspend fun processMainActivityIntentForNavigationSuspend(
    activity: ComponentActivity,
    nav: NavHostController,
    container: AppContainer,
) {
    val intent = activity.intent ?: return
    if (intent.action == PushMessageNotifier.ACTION_OPEN_MESSAGE) {
        val roomId = intent.getStringExtra(PushMessageNotifier.EXTRA_ROOM_ID).orEmpty()
        if (roomId.isNotBlank()) {
            val e = URLEncoder.encode(roomId, StandardCharsets.UTF_8.name())
            nav.navigate("chat/$e") { launchSingleTop = true }
        }
        intent.removeExtra(PushMessageNotifier.EXTRA_ROOM_ID)
        intent.removeExtra(PushMessageNotifier.EXTRA_MESSAGE_ID)
        if (intent.action == PushMessageNotifier.ACTION_OPEN_MESSAGE) {
            intent.action = Intent.ACTION_MAIN
        }
        return
    }
    val invite = CallNotificationParser.readInvite(intent) ?: return
    IncomingCallNotifier.cancel(activity.applicationContext, invite.callId)
    when (intent.getStringExtra(CallNotificationParser.EXTRA_ACTION).orEmpty()) {
        CallNotificationParser.ACTION_ACCEPT -> {
            if (CallCoordinator.prepareForIntentNavigation(CallCommand.Accept(invite))) {
                val merged = enrichInviteFromSocketIfNeeded(container, invite)
                val route =
                    "call/${url(merged.callId)}/${url(merged.roomId)}/${url(merged.fromUserId)}/${url(merged.fromName)}/1/1"
                nav.navigate(route) { launchSingleTop = true }
            }
        }
        CallNotificationParser.ACTION_DECLINE -> {
            if (CallCoordinator.prepareForIntentNavigation(CallCommand.Decline(invite))) {
                container.chatRepository.rejectCall(invite.callId)
            }
        }
        else -> {
            if (CallCoordinator.prepareForIntentNavigation(CallCommand.IncomingInvite(invite))) {
                val merged = enrichInviteFromSocketIfNeeded(container, invite)
                val route =
                    "call/${url(merged.callId)}/${url(merged.roomId)}/${url(merged.fromUserId)}/${url(merged.fromName)}/1/0"
                nav.navigate(route) { launchSingleTop = true }
            }
        }
    }
    CallNotificationParser.removeInviteExtras(intent)
}

private fun url(v: String): String = URLEncoder.encode(v, StandardCharsets.UTF_8.name())
