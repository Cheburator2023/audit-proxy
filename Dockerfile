FROM ubuntu:latest
LABEL authors="Viktor_Gabbasov"

ENTRYPOINT ["top", "-b"]