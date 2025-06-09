package com.example.chonline

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.chonline.network.fetchGroups
import com.example.chonline.ui.theme.Black10
import com.example.chonline.ui.theme.Orange1

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var groupList by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        fetchGroups(context) { result ->
            if (result.isSuccess) {
                groupList = result.getOrNull()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Ошибка загрузки данных"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Выбрать объект") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            if (groupList != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groupList!!) { (groupId, groupName) -> // ✅ Теперь передаём (ID, Name)
                        Button(
                            onClick = {
                                val intent = Intent(context, PhotoActivity::class.java)
                                intent.putExtra("GROUP_ID", groupId) // ✅ Передаём ID группы
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Black10)
                        ) {
                            Text(groupName, color = Orange1)
                        }
                    }
                }
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
        }
    }
}


