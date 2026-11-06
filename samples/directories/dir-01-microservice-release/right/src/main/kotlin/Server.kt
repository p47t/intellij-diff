package service

class Server(val port: Int = 9090, val tls: Boolean = true) {
    fun start() = println("Listening securely on $port (tls=$tls)")
}
