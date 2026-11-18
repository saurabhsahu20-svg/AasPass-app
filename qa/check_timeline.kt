import com.aaspass.app.data.TimelineMerge

data class Message(val id: String, val text: String)
fun main() {
    val live = listOf(Message("a", "edited"), Message("b", ""))
    val old = listOf(Message("a", "stale"), Message("b", "deleted text"), Message("c", "old"))
    val result = TimelineMerge.latestWins(live, old) { it.id }
    check(result.map { it.id } == listOf("a", "b", "c"))
    check(result[0].text == "edited" && result[1].text.isEmpty())
    check(TimelineMerge.latestWins(emptyList<Message>(), emptyList()) { it.id }.isEmpty())
    println("PASS: timeline merge tests (3 assertions)")
}
