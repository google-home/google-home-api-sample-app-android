/* Copyright 2025 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.example.googlehomeapisampleapp.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.googlehomeapisampleapp.R
import com.example.googlehomeapisampleapp.viewmodel.HomeAppViewModel

@Composable
fun WelcomeView(homeAppVM: HomeAppViewModel) {
  val context = LocalContext.current
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(32.dp), verticalArrangement = Arrangement.Center
  ) {

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
      Text(stringResource(R.string.welcome_text_1), fontSize = 32.sp)
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
      Text(stringResource(R.string.welcome_text_2), fontSize = 32.sp)
    }

    Spacer(Modifier.height(32.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
      Image(
        painter = painterResource(R.mipmap.ic_app_logo),
        contentDescription = stringResource(R.string.app_name)
      )
    }

    Spacer(Modifier.height(32.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
      Text(stringResource(R.string.welcome_text_3), fontSize = 24.sp, textAlign = TextAlign.Center)
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
      Text(stringResource(R.string.welcome_text_4), fontSize = 24.sp, textAlign = TextAlign.Center)
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
      Text(stringResource(R.string.welcome_text_5), fontSize = 24.sp, textAlign = TextAlign.Center)
    }

    Spacer(Modifier.height(32.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
      // Request Permission:
      //  - Trigger Permissions API and start the sign-in flow:
      //  - If a user is already signed in, this will just request permissions.
      //  - If not, this will trigger the account selection flow.
      Button(onClick = {
        homeAppVM.homeApp.permissionsManager.requestPermissions()
      }) {
        Text("Request Permission")
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
      // Google Sign-In:
      //  - Trigger Google Sign-In flow using Credential Manager.
      //  - This will allow the user to select a Google account.
      //  - Upon successful account selection, the HomeClient will be updated with the new account.
      //  - A proxy activity will be launched as the transition to recreate MainActivity
      //  - After MainActivity is created, it needs to request the permission for the new account
      Button(onClick = {
        homeAppVM.signInWithGoogleAccount(context)
      }) {
        Text("Google Sign-In")
      }
    }
  }
}