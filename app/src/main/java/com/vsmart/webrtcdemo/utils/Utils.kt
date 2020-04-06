package com.vsmart.webrtcdemo.utils

import com.vsmart.webrtcdemo.data.online.ITurnServer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class Utils {
    private var retrofitInstance: Retrofit? = null

    companion object {
        const val API_ENDPOINT = "https://global.xirsys.net"
        private var instance: Utils? = null

        fun getInstance(): Utils {
            if (instance == null) {
                instance = Utils()
            }
            return instance!!
        }
    }

    fun getRetrofitInstance(): ITurnServer {
        if (retrofitInstance == null) {
            retrofitInstance = Retrofit.Builder()
                .baseUrl(API_ENDPOINT)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }

        return retrofitInstance!!.create(ITurnServer::class.java)
    }
}