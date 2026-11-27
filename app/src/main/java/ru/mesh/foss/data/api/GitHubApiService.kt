package ru.mesh.foss.data.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import ru.mesh.foss.data.model.GitHubRelease

/** Публичный GitHub API для проверки релизов без токена. */
interface GitHubApiService {

    @GET("repos/fourtexec/MESHFOSS/releases")
    fun releases(
        @Query("per_page") perPage: Int,
        @Header("Accept") accept: String,
        @Header("User-Agent") userAgent: String,
    ): Call<List<GitHubRelease>>
}
