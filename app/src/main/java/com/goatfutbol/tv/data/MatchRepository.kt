package com.goatfutbol.tv.data

import com.goatfutbol.tv.api.GitHubClient
import com.goatfutbol.tv.api.MatchData
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MatchRepository {
    // CAMBIAR ESTOS VALORES POR LOS DE TU REPO
    private val OWNER = "calgpy" 
    private val REPO = "gf" 

    fun getMatch(onResult: (Result<MatchData>) -> Unit) {
        GitHubClient.service.getMatchData(OWNER, REPO).enqueue(object : Callback<MatchData> {
            override fun onResponse(call: Call<MatchData>, response: Response<MatchData>) {
                if (response.isSuccessful && response.body() != null) {
                    onResult(Result.success(response.body()!!))
                } else {
                    onResult(Result.failure(Exception("HTTP Error: ${response.code()} ${response.message()}")))
                }
            }

            override fun onFailure(call: Call<MatchData>, t: Throwable) {
                onResult(Result.failure(t))
            }
        })
    }
}
