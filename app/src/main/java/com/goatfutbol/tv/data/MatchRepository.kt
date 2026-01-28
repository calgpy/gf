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

    fun getMatch(onResult: (MatchData?) -> Unit) {
        GitHubClient.service.getMatchData(OWNER, REPO).enqueue(object : Callback<MatchData> {
            override fun onResponse(call: Call<MatchData>, response: Response<MatchData>) {
                if (response.isSuccessful) {
                    onResult(response.body())
                } else {
                    onResult(null)
                }
            }

            override fun onFailure(call: Call<MatchData>, t: Throwable) {
                onResult(null)
            }
        })
    }
}
