import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Voice Call Application",
        state = WindowState(width = 600.dp, height = 800.dp)
    ) {
        VoiceCallApp()
    }
}
