import com.aaspass.app.navigation.NotificationDestination

fun main() {
    val id = "Abc123Def456Ghi789Jk"
    require(NotificationDestination.fromPayload(id, null, null)?.route == "call/$id")
    require(NotificationDestination.fromPayload(null, "abc_DEF-123", null)?.route == "chat/abc_DEF-123")
    require(NotificationDestination.fromPayload(null, null, id)?.route == "group/$id")
    require(NotificationDestination.fromPayload(null, null, null, "study")?.route == "home?tab=3")
    require(NotificationDestination.fromPayload(null, null, null, "general")?.route == "home?tab=2")
    require(NotificationDestination.fromPayload("../../etc", null, null) == null)
    require(NotificationDestination.fromPayload(null, "a/b", null) == null)
    require(NotificationDestination.fromPayload(null, null, "a%2Fb") == null)
    require(NotificationDestination.fromPayload(null, null, null, "unknown") == null)
    println("PASS: navigation validation checks (9 assertions)")
}
