import kotlinx.coroutines.runBlocking

fun main() {
    println("Starting test voice server...")
    runBlocking {
        NetworkManager.startTestServer(8080)
    }
}
