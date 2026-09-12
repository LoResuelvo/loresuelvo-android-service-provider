package com.loresuelvo.serviceprovider.bdd.profilephoto

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.category.CategoryRepository
import com.loresuelvo.serviceprovider.domain.profile.PhotoValidationOutcome
import com.loresuelvo.serviceprovider.domain.profile.ProfilePhotoPreparer
import com.loresuelvo.serviceprovider.domain.profile.SelectedProfilePhoto
import com.loresuelvo.serviceprovider.domain.provider.ProviderRegistrationCommand
import com.loresuelvo.serviceprovider.domain.provider.ProviderRepository
import com.loresuelvo.serviceprovider.domain.provider.RegistrationOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeCategoryRepository : CategoryRepository {
    var outcome: CategoriesOutcome = CategoriesOutcome.Success(
        listOf(
            Category(1, "Plomería"),
            Category(2, "Electricidad"),
            Category(3, "Pintura"),
        ),
    )

    override suspend fun getCategories(): CategoriesOutcome = outcome
}

class FakeProviderRepository : ProviderRepository {
    var outcome: RegistrationOutcome = RegistrationOutcome.Success(providerId = 1)
    var registerCalls: Int = 0
        private set
    var lastCommand: ProviderRegistrationCommand? = null
        private set
    var registerGate: CompletableDeferred<Unit>? = null

    override suspend fun register(command: ProviderRegistrationCommand): RegistrationOutcome {
        registerCalls += 1
        lastCommand = command
        registerGate?.await()
        return outcome
    }
}

class FakeAuthSessionStore : AuthSessionStore {
    private val _session = MutableStateFlow<AuthSession?>(null)
    override val sessionFlow: StateFlow<AuthSession?> = _session

    override fun getSession(): AuthSession? = _session.value

    override fun saveSession(session: AuthSession) {
        _session.value = session
    }

    override fun clearSession() {
        _session.value = null
    }
}

class FakeProfilePhotoPreparer : ProfilePhotoPreparer {
    var outcome: PhotoValidationOutcome = PhotoValidationOutcome.Valid(
        SelectedProfilePhoto(
            originalName = "valid_profile.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 1024L,
            localPath = "/cache/valid_profile.jpg",
        ),
    )

    override suspend fun preparePhoto(source: String): PhotoValidationOutcome = outcome

    override suspend fun cleanPhoto(photo: SelectedProfilePhoto) {}
}
