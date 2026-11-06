package service

class Server(val port: Int = 8080) {
    fun start() = println("Listening on $port")
}
