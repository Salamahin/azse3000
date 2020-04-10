# azse3000
[![Build Status](https://travis-ci.com/Salamahin/azse3000.svg?branch=master)](https://travis-ci.com/Salamahin/azse3000)

This is surely not an Azure Storage Explorer (3000)

Provides bash-like syntax to operate on top of BlockBlobs in Azure

## Supported syntax

Basic syntax supports 3 operations: copy, move and remove

Here are some samples:
```
cp https://account.blob.core.windows.net/container/src https://account.blob.core.windows.net/container/dst
mv container@account/src1 container@account/src2 container@account/dst
rm {container1,container2}@account/src
```

Note that for shorten path version additional [configuration](#configuration) required.

Curly braces are expanded first and transform
```
[prefix]{expr1,expr2}[postfix]
```
to 
```
[prefix]expr1[postfix] [prefix]expr2[postfix]
```

Every path within a single command is processed in parallel mode, while execution 
of commands separated by *&&* is sequential

## Configuration
Optional file named secrets.conf is used for containing sensitive information
```conf
known-hosts: {
    myenv: https://account.blob.core.windows.net
}

known-secrets: {
    myenv: {
        container: "sas-token"
    }
}
```

After setting a host in *known-hosts* one may specify paths for command in the
following format:
```
cp container@myenv:/from container@myenv:/to
```
or even shorter
```
cp container@myenv:/{from,to}
```

The software will prompt a secret for the path unless it specified 
in *known-secrets* section

## Listing mode

Two possible listing mode supported: *batched* & *recursive*. Looks like recursive 
should work faster but creates additional pressure on Azure services.
Proper configuration are:

Config listing mode in *secrets.conf*
```
# For batching mode
listing-mode: {
  type: flat-limited
  max-fetch-blobs: 100
}

# For recursive mode
listing-mode: {
  type: recursive
}
```