package service

object LegacyAuth {
    fun checkBasic(token: String) = token.isNotEmpty()
}
