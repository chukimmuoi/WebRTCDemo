package com.vsmart.webrtcdemo.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class TurnServer {

    @SerializedName("s")
    @Expose
    var s: String = ""

    @SerializedName("p")
    @Expose
    var p: String = ""

    @SerializedName("e")
    @Expose
    var e: Any = ""

    @SerializedName("v")
    @Expose
    var iceServerList: IceServerList = IceServerList()

    inner class IceServerList {
        @SerializedName("iceServers")
        @Expose
        var iceServers: List<IceServer> = listOf()
    }
}