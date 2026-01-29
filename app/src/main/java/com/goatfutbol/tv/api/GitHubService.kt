package com.goatfutbol.tv.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GitHubService {
    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    fun triggerWorkflow(
        @Header("Authorization") token: String,
        @Header("Accept") accept: String = "application/vnd.github.v3+json",
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: String,
        @Body body: WorkflowDispatchBody
    ): Call<Void>

    @GET("repos/{owner}/{repo}/contents/data.json")
    fun getMatchData(
        @Header("Authorization") token: String,
        @Header("Accept") accept: String = "application/vnd.github.raw",
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Call<MatchData>
}

data class WorkflowDispatchBody(val ref: String = "main")

data class MatchData(
    val title: String,
    val url: String,
    val date: String,
    val error: String?
)
