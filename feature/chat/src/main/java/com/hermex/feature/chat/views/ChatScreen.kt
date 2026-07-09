// ReasoningBlock.kt
@Composable
fun ReasoningBlock(
    reasoning: Reasoning,
    onToggle: () -> Unit,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = if (reasoning.isExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (reasoning.isExpanded) "Collapse" else "Expand",
                    tint = colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = if (reasoning.isExpanded) "Hide Reasoning" else "Show Reasoning",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.primary
                )
            }
            
            if (reasoning.isExpanded) {
                Divider(
                    color = colorScheme.outline.copy(alpha = 0.3f)
                )
                
                MarkdownRenderer(
                    content = reasoning.content,
                    isStreaming = false,
                    colorScheme = colorScheme,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}