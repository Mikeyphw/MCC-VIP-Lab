#!/usr/bin/env python3
"""Offline-only exercise of callback-only vs verified credit policies.

This is NOT Meu Cronograma Capilar's source or actual diamond store.
It never talks to Google Ads, RevenueCat, Nhost, or an Android device.
"""
from dataclasses import dataclass, field

REWARD = 3


@dataclass
class Fixture:
    naive_diamonds: int = 0
    verified_diamonds: int = 0
    used_transactions: set = field(default_factory=set)

    def client_callback(self, ad_id: int) -> None:
        # Deliberately vulnerable example: client callback alone controls balance.
        self.naive_diamonds += REWARD
        # Verified balance NEVER changes here.

    def verified_ssv(self, transaction_id: str, valid: bool) -> None:
        if not valid or transaction_id in self.used_transactions:
            return
        self.used_transactions.add(transaction_id)
        self.verified_diamonds += REWARD


def run() -> dict:
    f = Fixture()
    f.client_callback(7)  # synthetically forced after a blocked / failed load
    fake_once = (f.naive_diamonds, f.verified_diamonds)
    f.client_callback(7)  # duplicate synthetic delivery
    fake_twice = (f.naive_diamonds, f.verified_diamonds)
    f.verified_ssv('test-receipt-001', True)  # standalone offline verification fixture
    f.verified_ssv('test-receipt-001', True)
    f.verified_ssv('test-receipt-002', False)
    result = {
        'fake_callback_once': fake_once,
        'fake_callback_duplicate': fake_twice,
        'verified_fixture_final': (f.naive_diamonds, f.verified_diamonds),
    }
    assert result == {
        'fake_callback_once': (3, 0),
        'fake_callback_duplicate': (6, 0),
        'verified_fixture_final': (6, 3),
    }, result
    return result


if __name__ == '__main__':
    print('MCC_P03_SYNTHETIC_CALLBACK_FIXTURE_PASS')
    for case, values in run().items():
        print(f'{case} naive={values[0]} verified={values[1]}')
    print('NOTE: OFFLINE MODEL ONLY. NOT AN OBSERVED MCC DIAMOND CREDIT.')
