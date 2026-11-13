#!/usr/bin/env python3
"""Locally create one stable signing keystore and optionally install GH encrypted secrets.
No Gradle runs in Termux. Never prints passwords, private key bytes or secret values.
"""
from __future__ import annotations
import argparse, base64, json, os, secrets, shutil, subprocess
from pathlib import Path

REPO = 'Mikeyphw/MCC-VIP-Lab'
HOME = Path.home() / '.local/share/mcc-vip-lab/signing'


def run(cmd, *, data=None):
    result = subprocess.run(cmd, input=data, capture_output=True, text=True)
    if result.returncode:
        raise SystemExit('PERSONAL_SIGNING_REFUSED: ' + cmd[0] +
                         ' exited ' + str(result.returncode) +
                         ' (check local tool access; secrets are not printed)')


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--publish-secrets', action='store_true',
                    help='Send signing key and passwords to encrypted GitHub repository secrets')
    args=ap.parse_args()
    if shutil.which('keytool') is None:
        raise SystemExit('PERSONAL_SIGNING_REFUSED: keytool missing; install OpenJDK in Termux')
    HOME.mkdir(parents=True,exist_ok=True)
    os.chmod(HOME, 0o700)
    keyfile=HOME/'mcc-personal.jks'
    metadata=HOME/'signing.json'
    if keyfile.exists() != metadata.exists():
        raise SystemExit('PERSONAL_SIGNING_REFUSED: incomplete existing key material')
    if not keyfile.exists():
        password=secrets.token_urlsafe(28)
        alias='mcc-personal'
        run(['keytool','-genkeypair','-noprompt','-alias',alias,
             '-keyalg','RSA','-keysize','3072','-validity','3650',
             '-keystore',str(keyfile),'-storetype','PKCS12',
             '-storepass',password,'-keypass',password,
             '-dname','CN=MCC Personal, O=Personal, C=BR'])
        os.chmod(keyfile,0o600)
        with metadata.open('x') as stream:
            os.chmod(metadata,0o600)
            json.dump({'alias':alias,'password':password},stream)
        print('MCC_PERSONAL_SIGNING_KEY_CREATED_LOCAL_ONLY')
    else:
        print('MCC_PERSONAL_SIGNING_KEY_REUSED_LOCAL_ONLY')
    if args.publish_secrets:
        if shutil.which('gh') is None:
            raise SystemExit('PERSONAL_SIGNING_REFUSED: gh missing')
        stored=json.loads(metadata.read_text())
        pairs={
            'MCC_PERSONAL_KEYSTORE_B64':base64.b64encode(keyfile.read_bytes()).decode(),
            'MCC_PERSONAL_STORE_PASSWORD':stored['password'],
            'MCC_PERSONAL_KEY_ALIAS':stored['alias'],
            'MCC_PERSONAL_KEY_PASSWORD':stored['password']}
        for key,val in pairs.items():
            run(['gh','secret','set',key,'-R',REPO],data=val)
        print('MCC_PERSONAL_SIGNING_FOUR_ENCRYPTED_REPOSITORY_SECRETS_SET')
    print('MCC_PERSONAL_SIGNING_KEY_LOCATION='+str(keyfile))
    print('MCC_PERSONAL_SIGNING_NEVER_COMMIT_OR_SHARE_SIGNING_DIRECTORY')

if __name__=='__main__': main()
