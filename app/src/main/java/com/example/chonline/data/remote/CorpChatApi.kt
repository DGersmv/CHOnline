package com.example.chonline.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface CorpChatApi {

    @POST("auth/send")
    suspend fun sendCode(@Body body: SendCodeRequest): SendCodeResponse

    @POST("auth/verify")
    suspend fun verify(@Body body: VerifyRequest): VerifyResponse

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

    @GET("rooms")
    suspend fun rooms(): RoomsResponse

    @POST("rooms/dm")
    suspend fun createDm(@Body body: CreateDmRequest): CreateDmResponse

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

    @Multipart
    @POST("rooms/{roomId}/messages/file")
    suspend fun sendFile(
        @Path("roomId", encoded = true) roomId: String,
        @Part file: MultipartBody.Part,
        @Part("text") text: RequestBody?,
        @Part("originalFilename") originalFilename: RequestBody?,
    ): SendMessageResponse
}
