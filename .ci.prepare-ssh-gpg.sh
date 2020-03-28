#!/usr/bin/env bash
set -ex
# Import SSH key to access GitHub for versioning
openssl aes-256-cbc -K "${encrypted_96e73e3cb232_key}" -iv "${encrypted_96e73e3cb232_iv}" \
    -in id_rsa_dockerfile_image_update.enc -out id_rsa_dockerfile_image_update -d
mkdir -p "${HOME}/.ssh"
mv -f id_rsa_dockerfile_image_update "${HOME}/.ssh/id_rsa"
echo "github.com ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEAq2A7hRGmdnm9tUDbO9IDSwBK6TbQa+PXYPCPy6rbTrTtw7PHkccKrpp0yVhp5HdEIcKr6pLlVDBfOLX9QUsyCOV0wzfjIJNlGEYsdlLJizHhbn2mUjvSAHQqZETYP81eFzLQNnPHt4EVVUh7VfDESU84KezmD5QlWpXLmvU31/yMf+Se8xhHTvKSCZIFImWwoG6mbUoWf9nzpIoaSjB+weqqUUmpaaasXVal72J+UX2B+2RPW3RcT0eOzQgqlJL3RKrTJvdsjE3JEAvGq3lGHSZXy28G3skua2SmVi/w4yCE6gbODqnTWlg7+wC604ydGXA8VJiS5ap43JXiUFFAaQ==" >> "${HOME}/.ssh/known_hosts"

# Import code signing keys
openssl aes-256-cbc -K "${encrypted_00fae8efff8c_key}" -iv "${encrypted_00fae8efff8c_iv}" -in codesigning.asc.enc -out codesigning.asc -d
gpg --no-tty --batch --yes --fast-import codesigning.asc

# Allow loopback pinentry in maven-gpg-plugin (ain't nobody need no shared tty)
echo "allow-loopback-pinentry" >> ~/.gnupg/gpg-agent.conf
gpgconf --reload gpg-agent

# Remove code signing keys (since the releaser plugin requires a clean git workspace)
shred --remove codesigning.asc
