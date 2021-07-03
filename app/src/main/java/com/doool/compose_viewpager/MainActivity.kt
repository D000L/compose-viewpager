package com.doool.compose_viewpager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doool.compose_viewpager.ui.theme.ComposeviewpagerTheme
import com.doool.viewpager.ViewPager
import com.doool.viewpager.ViewPagerOrientation
import com.doool.viewpager.ViewPagerTransformer
import com.doool.viewpager.transformers.DefaultTransformer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComposeviewpagerTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    ViewPagers()
                }
            }
        }
    }
}

@Composable
fun ViewPagers() {
    Column {
        var expended by remember { mutableStateOf(false) }

        val transformerList = listOf<ViewPagerTransformer>(
            DefaultTransformer()
        )

        var transformer: ViewPagerTransformer by remember { mutableStateOf(DefaultTransformer()) }

        Row(Modifier.fillMaxWidth()) {
            Text(text = transformer::class.java.simpleName, fontSize = 10.sp)
        }

        DropdownMenu(expanded = expended, onDismissRequest = { expended = false }) {
            transformerList.forEach {
                DropdownMenuItem(onClick = {
                    transformer = it
                    expended = false
                }) {
//                    DefaultText(text = it::class.java.simpleName)
                }
            }
        }

        ViewPager(
            modifier = Modifier
                .background(Color.Gray)
                .height(500.dp),
            items = (0..5).toList(),
            orientation = ViewPagerOrientation.Horizontal,
            transformer = transformer
        ) {
            val position = getPagePosition()

            Box(
                modifier = Modifier
//                .size(200.dp, 300.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Card $it"
                    )

                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentSize(),
                        fontSize = 10.sp,
                        text = "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod"
                    )
                }
            }
        }
    }
}
