sealed class SkillsNavRoute(val route: String) {
    object Home : SkillsNavRoute("skills")
    object Detail : SkillsNavRoute("skills/{skillId}") {
        fun createRoute(skillId: String) = "skills/$skillId"
    }
}

@Composable
fun SkillsNavGraph(
    onNavigateToSkill: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    NavHost(
        navController = rememberNavController(),
        startDestination = SkillsNavRoute.Home.route
    ) {
        composable(SkillsNavRoute.Home.route) {
            SkillsView(
                server = remember { URL("https://hermes-server.com") },
                onNavigateToSkill = onNavigateToSkill
            )
        }
        composable(SkillsNavRoute.Detail.route) { backStackEntry ->
            val skillId = backStackEntry.arguments?.getString("skillId")
            skillId?.let {
                SkillDetailView(
                    skillId = it,
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}