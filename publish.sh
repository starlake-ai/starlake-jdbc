#!/bin/bash
set -e

if [[ "$RELEASE" == "1" || "$RELEASE" == "true" ]]; then
  echo "Release mode detected."

  # Ensure the workspace is clean (ignoring untracked files)
  if [[ -n $(git status --porcelain -uno) ]]; then
    echo "Error: Workspace is not clean. Please commit or stash changes before releasing."
    exit 1
  fi

  # Extract the current release version from Gradle (simulating release)
  echo "Determining next release version..."
  CURRENT_VERSION=$(RELEASE=true ./gradlew properties -q | grep "^version:" | awk '{print $2}')

  if [ -z "$CURRENT_VERSION" ]; then
    echo "Error: Could not extract version from Gradle."
    exit 1
  fi

  # Determine Release Version
  # Since we passed RELEASE=true, Gradle already gives us the target release version
  RELEASE_VERSION=$CURRENT_VERSION

  echo "Preparing to release version: $RELEASE_VERSION"

  # Check if tag already exists (try with and without v prefix)
  TAG_NAME=$RELEASE_VERSION
  # If the version doesn't start with v, we might want to tag it as vVERSION (standard practice),
  # but starlake-jdbc tags seem to be just the version number or vVERSION?
  # Let's check existing tags first.
  # The user's jsqltranspiler script uses "v$RELEASE_VERSION".
  TAG="v$RELEASE_VERSION"
  
  if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo "Error: Tag $TAG already exists."
    exit 1
  fi

  # Create Tag
  echo "Creating tag $TAG..."
  git tag -a "$TAG" -m "Release $RELEASE_VERSION"

  # Run the publish task
  echo "Publishing release $RELEASE_VERSION..."
  # Pass RELEASE=true to Gradle to ensure it builds the release version
  RELEASE=true ./gradlew --info clean publish --no-build-cache --refresh-dependencies

  # Push the tag (only if publish succeeded)
  echo "Pushing tag $TAG..."
  git push origin "$TAG"

  echo "Release $RELEASE_VERSION published and tagged successfully."

else
  echo "Snapshot mode detected (RELEASE not set)."
  echo "Publishing snapshot..."
  ./gradlew --info clean publish --no-build-cache --refresh-dependencies
  echo "Snapshot published successfully."
fi