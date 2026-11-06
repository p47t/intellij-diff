package service

class OAuth2Provider(val issuerUrl: String) {
    fun verifyJwt(jwt: String): Boolean = jwt.startsWith("eyJ")
}
