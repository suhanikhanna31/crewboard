package com.crewboard.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class TaskDto(
    val id: Int,
    val title: String,
    val description: String = "",
    @SerialName("required_skill") val requiredSkill: String = "",
    val zone: String = "",
    val priority: Int = 1,
    val status: String,
    @SerialName("assigned_to") val assignedTo: Int? = null,
)

@Serializable
data class ResourceDto(
    val id: Int,
    val name: String,
    val kind: String,
    val skills: String = "",
    val zone: String = "",
    val status: String,
    val battery: Double = 100.0,
)

@Serializable data class LoginReq(val username: String, val password: String)

@Serializable
data class TokenResp(
    @SerialName("access_token") val accessToken: String,
    val role: String = "worker",
    @SerialName("resource_id") val resourceId: Int? = null,
)

@Serializable data class StatusReq(val status: String)

@Serializable
data class IssueReq(val message: String, @SerialName("task_id") val taskId: Int? = null)

fun TaskDto.toEntity() = TaskEntity(id, title, description, requiredSkill, zone, priority, status, assignedTo)

interface CrewApi {
    @POST("auth/login") suspend fun login(@Body body: LoginReq): TokenResp
    @GET("tasks") suspend fun tasks(@Query("assigned_to") assignedTo: Int? = null): List<TaskDto>
    @PATCH("tasks/{id}/status") suspend fun setStatus(@Path("id") id: Int, @Body body: StatusReq): TaskDto
    @POST("issues") suspend fun issue(@Body body: IssueReq)
    @GET("resources") suspend fun resources(): List<ResourceDto>
}

object ApiFactory {
    val json = Json { ignoreUnknownKeys = true }

    fun create(baseUrl: String, token: () -> String?): CrewApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                token()?.let { req.header("Authorization", "Bearer $it") }
                chain.proceed(req.build())
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CrewApi::class.java)
    }
}
