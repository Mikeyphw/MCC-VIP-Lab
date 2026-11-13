package dev.mikey.mccviplab;

import static org.junit.Assert.*;
import org.junit.Test;

public class CosmeticPolicyTest {
    @Test public void exactKnownUpsellsOnly() {
        assertTrue(CosmeticPolicy.isUpsell("Assine Premium"));
        assertTrue(CosmeticPolicy.isUpsell("Quero assinar Premium"));
        assertTrue(CosmeticPolicy.isUpsell("Remova propagandas e tenha diamantes infinitos"));
        assertTrue(CosmeticPolicy.isUpsell("Diamantes infinitos e sem anúncios por R$ 9,90"));
        assertTrue(CosmeticPolicy.isUpsell("Suscríbete a Premium"));
    }
    @Test public void keepFunctionalElementsAndAccountStatus() {
        for (String s : new String[]{"Premium", "Assinante Premium", "Gerenciar assinaturas",
                "Não assinante", "Veja um anúncio para ganhar 3 diamantes",
                "Você deve ver o anúncio até a recompensa ser creditada!",
                "Diamantes", "Recompensas", "Comprar produtos", "", "Dia Premium"}) {
            assertFalse(s, CosmeticPolicy.isUpsell(s));
        }
    }
    @Test public void rejectLongCompositeLabels() {
        StringBuilder s = new StringBuilder("Assine Premium ");
        while (s.length() < 150) s.append("somente este mes ");
        assertFalse(CosmeticPolicy.isUpsell(s));
    }
}
