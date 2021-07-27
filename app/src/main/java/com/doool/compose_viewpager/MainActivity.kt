package com.doool.compose_viewpager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doool.compose_viewpager.transformer.compose.CardFlipPageTransformer
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
        val transformer by remember {
            mutableStateOf(CardFlipPageTransformer())
        }

        ViewPager(
                modifier = Modifier
                        .background(Color.Gray)
                        .fillMaxSize(),
                orientation = ViewPagerOrientation.Horizontal,
                transformer = transformer
        ) {
            items(10) {
                val offset = getPagePosition()
                ItemSample(it)
            }

            items(10) {
                val offset = getPagePosition()
                ItemSample(it)
            }
        }
    }
}

@Composable
fun ItemSample(index :Int) {
    Box(
            modifier = Modifier
                    .width(200.dp)
                    .shadow(
                            elevation = 0.dp,
                            shape = RoundedCornerShape(0.dp)
                    )
                    .background(
                            color = Color.White,
                            shape = RoundedCornerShape(0.dp)
                    )
    ) {
        Column(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                    text = "Card $1"
            )

            Text(
                    modifier = Modifier
                            .weight(1f)
                            .wrapContentSize(),
                    fontSize = 10.sp,
                    text = "$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index$index"
            )
        }
    }
}

