@Composable
fun OnboardingScreen(
    onboardingViewModel: OnboardingViewModel,
    onConnectSuccess: () -> Unit
) {
    val state = onboardingViewModel.onboardingState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { state.value.totalPages })

    // Enforce Dark Theme
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Pager for Pages 0-3, Connect is Page 4
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f)
                    ) { page ->
                        when (page) {
                            0 -> WelcomePage()
                            1 -> FeaturesPage()
                            2 -> AgentPromptPage()
                            3 -> TailscalePage()
                            4 -> ConnectPage(
                                serverUrl = state.value.serverUrl,
                                password = state.value.password,
                                isConnecting = state.value.isConnecting,
                                onServerUrlChange = onboardingViewModel::onServerUrlChange,
                                onPasswordChange = onboardingViewModel::onPasswordChange,
                                onConnect = { onboardingViewModel.connect(); onConnectSuccess() }
                            )
                        }
                    }

                    // Bottom Navigation / Progress
                    OnboardingBottomBar(
                        currentStep = state.value.currentStep,
                        onStepChange = onboardingViewModel::onStepChange
                    )
                }
            }
        }
    )
}

@Composable
private fun ConnectPage(
    serverUrl: String,
    password: String,
    isConnecting: Boolean,
    onServerUrlChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Connect",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("Server URL") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = "Server URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onConnect,
            enabled = serverUrl.isNotBlank() && !isConnecting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun WelcomePage() { Text("Welcome", modifier = Modifier.fillMaxSize()) }
@Composable
private fun FeaturesPage() { Text("Features", modifier = Modifier.fillMaxSize()) }
@Composable
private fun AgentPromptPage() { Text("Agent Prompt", modifier = Modifier.fillMaxSize()) }
@Composable
private fun TailscalePage() { Text("Tailscale", modifier = Modifier.fillMaxSize()) }

@Composable
private fun OnboardingBottomBar(
    currentStep: Int,
    onStepChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (currentStep > 0) {
            TextButton(onClick = { onStepChange(currentStep - 1) }) { Text("Back") }
        }
        if (currentStep < 4) {
            TextButton(onClick = { onStepChange(currentStep + 1) }) { Text("Next") }
        }
    }
}