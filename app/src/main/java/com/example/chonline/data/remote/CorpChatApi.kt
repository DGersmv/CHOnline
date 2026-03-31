package com.example.chonline.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface CorpChatApi {

    @GET("auth/login-type")
    suspend fun loginType(@Query("login") login: String): LoginTypeResponse

    @POST("auth/send")
    suspend fun sendCode(@Body body: SendCodeRequest): SendCodeResponse

    @POST("auth/verify")
    suspend fun verify(@Body body: VerifyRequest): VerifyResponse

    @POST("auth/client-login")
    suspend fun clientLogin(@Body body: ClientLoginRequest): ClientAuthResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): RefreshResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest): OkResponse

    @GET("me")
    suspend fun me(): MeResponse

    @PUT("profile")
    suspend fun updateProfile(@Body body: ProfileUpdateRequest): MeResponse

    @GET("employees")
    suspend fun employees(): EmployeesResponse

    /** История звонков 1:1 (только сотрудники). */
    @GET("calls")
    suspend fun calls(): CallsHistoryResponse

    @GET("rooms")
    suspend fun rooms(): RoomsResponse

    @GET("rooms/{roomId}/group-edit")
    suspend fun groupEdit(@Path("roomId", encoded = true) roomId: String): GroupEditResponse

    @PATCH("rooms/{roomId}")
    suspend fun patchRoom(
        @Path("roomId", encoded = true) roomId: String,
        @Body body: PatchRoomRequest,
    ): RoomMutationResponse

    @Multipart
    @POST("rooms/{roomId}/avatar")
    suspend fun uploadRoomAvatar(
        @Path("roomId", encoded = true) roomId: String,
        @Part photo: MultipartBody.Part,
    ): RoomMutationResponse

    @DELETE("rooms/{roomId}/avatar")
    suspend fun deleteRoomAvatar(@Path("roomId", encoded = true) roomId: String): RoomMutationResponse

    @POST("rooms/{roomId}/leave")
    suspend fun leaveRoom(@Path("roomId", encoded = true) roomId: String): OkResponse

    @DELETE("rooms/{roomId}")
    suspend fun deleteRoom(@Path("roomId", encoded = true) roomId: String): OkResponse

    @POST("rooms/dm")
    suspend fun createDm(@Body body: CreateDmRequest): CreateDmResponse

    /** Сотрудник открывает чат с заказчиком (общая портальная комната). */
    @POST("rooms/open-client")
    suspend fun openClient(@Body body: OpenClientRequest): CreateDmResponse

    @POST("rooms/group")
    suspend fun createGroup(@Body body: CreateGroupRequest): CreateDmResponse

    @GET("rooms/{roomId}/messages")
    suspend fun messages(
        @Path("roomId", encoded = true) roomId: String,
        @Query("limit") limit: Int = 50,
        @Query("before") before: String? = null,
    ): MessagesResponse

    @POST("rooms/{roomId}/messages")
    suspend fun sendText(
        @Path("roomId", encoded = true) roomId: String,
        @Body body: SendTextRequest,
    ): SendMessageResponse

    @PATCH("rooms/{roomId}/messages/{messageId}")
    suspend fun editMessage(
        @Path("roomId", encoded = true) roomId: String,
        @Path("messageId", encoded = true) messageId: String,
        @Body body: SendTextRequest,
    ): SendMessageResponse

    @DELETE("rooms/{roomId}/messages/{messageId}")
    suspend fun deleteMessage(
        @Path("roomId", encoded = true) roomId: String,
        @Path("messageId", encoded = true) messageId: String,
    ): OkResponse

    @Multipart
    @POST("rooms/{roomId}/messages/file")
    suspend fun sendFile(
        @Path("roomId", encoded = true) roomId: String,
        @Part file: MultipartBody.Part,
        @Part("text") text: RequestBody?,
        @Part("originalFilename") originalFilename: RequestBody?,
    ): SendMessageResponse

    // --- Заказчик ---
    @GET("client/me")
    suspend fun clientMe(): ClientProfileDto

    @PUT("client/profile")
    suspend fun updateClientProfile(@Body body: ClientProfileUpdateRequest): ClientProfileDto

    @GET("client/employees")
    suspend fun clientEmployees(): List<ClientEmployeeDto>

    @GET("client/rooms")
    suspend fun clientRooms(): RoomsResponse

    @POST("client/rooms/open-peer")
    suspend fun clientOpenPeer(@Body body: CreateDmRequest): CreateDmResponse

    @GET("client/rooms/{roomId}/messages")
    suspend fun clientMessages(
        @Path("roomId", encoded = true) roomId: String,
        @Query("limit") limit: Int = 50,
        @Query("before") before: String? = null,
    ): MessagesResponse

    @POST("client/rooms/{roomId}/messages")
    suspend fun clientSendText(
        @Path("roomId", encoded = true) roomId: String,
        @Body body: SendTextRequest,
    ): SendMessageResponse

    @PATCH("client/rooms/{roomId}/messages/{messageId}")
    suspend fun clientEditMessage(
        @Path("roomId", encoded = true) roomId: String,
        @Path("messageId", encoded = true) messageId: String,
        @Body body: SendTextRequest,
    ): SendMessageResponse

    @DELETE("client/rooms/{roomId}/messages/{messageId}")
    suspend fun clientDeleteMessage(
        @Path("roomId", encoded = true) roomId: String,
        @Path("messageId", encoded = true) messageId: String,
    ): OkResponse

    @Multipart
    @POST("client/rooms/{roomId}/messages/file")
    suspend fun clientSendFile(
        @Path("roomId", encoded = true) roomId: String,
        @Part file: MultipartBody.Part,
        @Part("text") text: RequestBody?,
        @Part("originalFilename") originalFilename: RequestBody?,
    ): SendMessageResponse

    // --- Админ: заказчики (employee + JWT администратора) ---
    @GET("admin/clients")
    suspend fun adminClients(): List<AdminClientDto>

    @POST("admin/clients")
    suspend fun adminCreateClient(@Body body: CreateAdminClientRequest): CreateAdminClientResponse

    @PUT("admin/clients/{id}")
    suspend fun adminPatchClient(
        @Path("id") id: String,
        @Body body: PatchAdminClientRequest,
    ): OkResponse

    @PUT("admin/clients/{id}/password")
    suspend fun adminSetClientPassword(
        @Path("id") id: String,
        @Body body: AdminClientPasswordRequest,
    ): OkResponse

    @GET("admin/clients/{id}/users")
    suspend fun adminClientUsers(@Path("id") id: String): List<AdminVisibleUserDto>

    @PUT("admin/clients/{id}/users")
    suspend fun adminPutClientUsers(
        @Path("id") id: String,
        @Body body: AdminClientUsersRequest,
    ): OkResponse

    @DELETE("admin/clients/{id}")
    suspend fun adminDeleteClient(@Path("id") id: String): OkResponse

    @GET("admin/employees")
    suspend fun adminEmployees(): List<AdminEmployeeRowDto>
}
