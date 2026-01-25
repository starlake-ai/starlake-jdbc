#git tag 0.4
#git push origin 0.4
#RELEASE=true ./publish.sh

gradle --info clean publish --no-build-cache  --refresh-dependencies