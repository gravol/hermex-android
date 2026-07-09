@Serializable
data class SessionWithItems(
    @SerialName("session_id")
    val sessionId: String,
    
    @SerialName("items")
    val items: List<SessionItem>,
    
    @SerialName("metadata")
    val metadata: Map<String, Any?>?
)

@Serializable
data class SessionItem(
    @SerialName("item_id")
    val itemId: String,
    
    @SerialName("quantity")
    val quantity: Int,
    
    @SerialName("price")
    val price: Double
)