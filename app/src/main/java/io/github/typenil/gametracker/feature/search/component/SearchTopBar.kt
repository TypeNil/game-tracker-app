package io.github.typenil.gametracker.feature.search.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.model.SearchInputValidation
import io.github.typenil.gametracker.core.model.SearchInputViolation

private fun SearchInputViolation.messageRes(): Int = when (this) {
    SearchInputViolation.TOO_LONG -> R.string.search_input_error_too_long
    SearchInputViolation.QUOTE_OR_BACKSLASH -> R.string.search_input_error_unsupported_chars
    SearchInputViolation.CONTROL_CHAR, SearchInputViolation.INVISIBLE_FORMAT -> R.string.search_input_error_invisible_chars
}

/**
 * Top search header bar with search text field, clear action, and input validation.
 *
 * A dedicated header row instead of TopAppBar(title = TextField): M3 TextField reserves
 * its supporting-text area even when empty, which grew the bar to ~73dp and left the
 * field with dead space before the filter row.
 */
@Composable
internal fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
    inputValidation: SearchInputValidation = SearchInputValidation.Valid(""),
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val focusManager = LocalFocusManager.current
    val searchFieldDescription = stringResource(R.string.search_action_desc)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            singleLine = true,
            isError = inputValidation is SearchInputValidation.Invalid,
            supportingText = (inputValidation as? SearchInputValidation.Invalid)?.let { invalid ->
                {
                    Text(
                        text = stringResource(invalid.violation.messageRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClearQuery) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.search_clear_desc),
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                },
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .semantics {
                    contentDescription = searchFieldDescription
                },
        )
    }
}
