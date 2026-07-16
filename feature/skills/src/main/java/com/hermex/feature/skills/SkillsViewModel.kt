@Composable
fun SkillDetailView(
    skillId: String,
    viewModel: SkillsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val skill = state.let {
        if (it is SkillsState.Loaded) {
            it.skills.find { s -> s.id == skillId }
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skill Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (skill != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = skill.name,
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Text(
                    text = skill.category.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                if (skill.description != null) {
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (skill.disabled) "Disabled" else "Enabled",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = !skill.disabled,
                            onCheckedChange = { viewModel.toggleSkill(skill, it) },
                            enabled = !viewModel.togglingSkillNames.value.contains(skill.id)
                        )
                    }
                }
            }
        } else {
            ContentUnavailable(
                title = "Skill Not Found",
                icon = Icons.Default.Error
            )
        }
    }
}