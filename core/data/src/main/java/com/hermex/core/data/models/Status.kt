@Serializable
     sealed class Status {
         @Serializable
         @SerialName("active")
         data object Active : Status()

         @Serializable
         @SerialName("inactive")
         data object Inactive : Status()
     }