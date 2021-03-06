/*
 * Copyright 2016 Yoav Sternberg.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.ohelshem.app.android.login

import com.ohelshem.api.model.UpdateError
import com.ohelshem.app.android.utils.BasePresenter
import com.ohelshem.app.controller.analytics.Analytics
import com.ohelshem.app.controller.api.ApiController
import com.ohelshem.app.controller.storage.Storage

class LoginPresenter(private val storage: Storage, private val apiController: ApiController, private val analytics: Analytics) : BasePresenter<LoginView>(), ApiController.Callback {
    private var lastPassword: String = ""
    private var lastId: String = ""


    override fun onCreate() {
        apiController[Id] = this
    }

    override fun onDestroy() {
        apiController -= Id

        lastPassword = ""
        lastId = ""
    }


    fun login(id: String, password: String) {
        if (!apiController.isBusy) {
            if ((id.length < 8 || id.length > 9) || !id.all(Char::isDigit)) view?.showIdInvalidError()
            else if (password.length < 4) view?.showPasswordInvalidError()
            else {
                lastPassword = password
                lastId = id
                apiController.setAuthData(id, password)
                apiController.login()
                view?.showLoading()
            }
        }
    }

    override fun onSuccess(apis: Set<ApiController.UpdatedApi>) {
        storage.password = lastPassword
        storage.id = lastId
        if (storage.userData.isTeacher()) {
            view?.showTeachersDialog(storage) {
                analytics.onLogin()
                view?.launchApp()
            }
        } else {
            view?.showStudentsDialog(storage) {
                analytics.onLogin()
                view?.launchApp()
            }
        }

        onDestroy()
    }

    override fun onFail(error: UpdateError) {
        view?.showLoginError(error)
    }

    override fun onChoosingClass() = Unit

    companion object {
        private const val Id = 532
    }
}