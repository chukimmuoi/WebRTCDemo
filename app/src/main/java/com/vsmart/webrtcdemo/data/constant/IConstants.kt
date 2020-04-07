package com.vsmart.webrtcdemo.data.constant

interface IConstants {
    companion object {
        const val IO_SOCKET_IP = "192.168.1.106"
        const val IO_SOCKET_PORT = 1794
        const val IO_SOCKET = "http://$IO_SOCKET_IP:$IO_SOCKET_PORT"

        const val EVENT_CREATE_OR_JOIN = "create or join"
        const val EVENT_CREATED = "created"
        const val EVENT_FULL    = "full"
        const val EVENT_JOIN    = "join"
        const val EVENT_JOINED  = "joined"
        const val EVENT_LOG     = "log"
        const val EVENT_BYE     = "bye"
        const val EVENT_MESSAGE = "message"

        const val RESULT_GOT_USER_MEDIA = "got user media"
        const val RESULT_BYE            = "bye"
        const val RESULT_TYPE           = "type"
        const val RESULT_OFFER          = "offer"
        const val RESULT_ANSWER         = "answer"
        const val RESULT_CANDIDATE      = "candidate"
        const val RESULT_SDP            = "sdp"
        const val RESULT_LABEL          = "label"
        const val RESULT_ID             = "id"

        const val TYPE_CANDIDATE = "candidate"
    }
}