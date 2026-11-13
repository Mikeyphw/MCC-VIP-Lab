package dev.mikey.mccviplab;

import java.text.Normalizer;
import java.util.Locale;

/** Pure-Java exact-label classifier. Never treats generic Premium/account status as an upsell. */
final class CosmeticPolicy {
    private CosmeticPolicy() {}

    static String normalize(CharSequence text) {
        if (text == null) return "";
        String s = Normalizer.normalize(text.toString(), Normalizer.Form.NFD);
        s = s.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return s.replaceAll("\\s+", " ").trim();
    }

    static boolean isUpsell(CharSequence text) {
        String s = normalize(text);
        if (s.length() == 0 || s.length() > 120) return false;
        switch (s) {
            case "assine premium":
            case "assinar premium":
            case "quero assinar premium":
            case "assine premium para diamantes infinitos":
            case "remova propagandas e tenha diamantes infinitos":
            case "premium na faixa? veja como aqui!":
            case "quer assinatura de graca?":
            case "suscribete a premium":
            case "quiero suscribirme a premium":
            case "elimina anuncios y obten diamantes infinitos":
            case "premium gratis? mira como aqui!":
            case "subscribe to premium":
            case "go premium":
            case "remove ads and get unlimited diamonds":
                return true;
            default:
                return s.startsWith("diamantes infinitos e sem anuncios por ") ||
                       s.startsWith("diamantes infinitos y sin anuncios por ") ||
                       s.startsWith("unlimited diamonds and no ads for ");
        }
    }
}
