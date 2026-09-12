package com.tungsten.fclcore.auth.microsoft;

import com.tungsten.fclcore.auth.AccountFactory;
import com.tungsten.fclcore.auth.AuthenticationException;
import com.tungsten.fclcore.auth.CharacterSelector;
import com.tungsten.fclcore.util.StringUtils;

import java.util.Map;
import java.util.Objects;

/**
 * Hazır bir Minecraft erişim jetonuyla giriş yapar.
 *
 * Microsoft'un tarayıcı/cihaz kodu akışını atlar: kullanıcı jetonu yapıştırır, jeton
 * Minecraft profil ucuna doğrulatılır ve dönen profille normal bir {@link MicrosoftAccount}
 * kurulur. Üretilen hesap her bakımdan Microsoft hesabı gibi davranır - tek farkı yenileme
 * jetonu olmamasıdır, yani jetonun süresi dolduğunda hesabın yeniden eklenmesi gerekir.
 *
 * Giriş tipi {@link AccountLoginType#USERNAME}: tek metin alanı kullanılır ve içine
 * kullanıcı adı değil jeton yazılır (bkz. CreateAccountDialog'daki TokenDetails).
 */
public class MicrosoftTokenAccountFactory extends AccountFactory<MicrosoftAccount> {

    private final MicrosoftService service;

    public MicrosoftTokenAccountFactory(MicrosoftService service) {
        this.service = service;
    }

    @Override
    public AccountLoginType getLoginType() {
        return AccountLoginType.USERNAME;
    }

    @Override
    public MicrosoftAccount create(CharacterSelector selector, String username, String password, ProgressCallback progressCallback, Object additionalData) throws AuthenticationException {
        if (StringUtils.isBlank(username)) {
            throw new AuthenticationException("Access token is empty");
        }
        // Kullanıcıların jetonu "Bearer xxx" ya da tırnak içinde yapıştırması sık görülüyor
        String token = username.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        token = StringUtils.removeSurrounding(token, "\"");

        MicrosoftSession session = service.authenticateWithAccessToken(token);
        return new MicrosoftAccount(service, session);
    }

    @Override
    public MicrosoftAccount fromStorage(Map<Object, Object> storage) {
        Objects.requireNonNull(storage);
        MicrosoftSession session = MicrosoftSession.fromStorage(storage);
        return new MicrosoftAccount(service, session);
    }
}
