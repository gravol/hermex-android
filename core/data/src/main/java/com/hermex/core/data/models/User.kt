@Serializable
data class User(
    @SerialName("user_id")
    val userId: String,
    
    @SerialName("profile")
    val profile: Profile?
)

@Serializable
data class Profile(
    @SerialName("display_name")
    val displayName: String?,
    
    @SerialName("avatar_url")
    val avatarUrl: String?
)