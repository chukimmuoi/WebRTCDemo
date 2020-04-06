package com.vsmart.webrtcdemo.data.online

import com.vsmart.webrtcdemo.model.TurnServer
import retrofit2.Call
import retrofit2.http.Header
import retrofit2.http.PUT

interface ITurnServer {
    @PUT("/_turn/MyFirstApp")
    fun getIceCandidates(@Header("Authorization") authKey: String): Call<TurnServer>
}