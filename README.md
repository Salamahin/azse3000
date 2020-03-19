# azse3000
This is surely not an Azure Storage Explorer (3000)

Provides bash-like syntax to operate on top of BlockBlobs in Azure

## Supported syntax

Basic syntax supports 3 operations: copy, move and remove

Here are some samples:
```
cp https://account.blob.core.windows.net/container/src https://account.blob.core.windows.net/container/dst
cp https://account.blob.core.windows.net/container/src1 https://account.blob.core.windows.net/container/src2 https://account.blob.core.windows.net/container/dst
mv container@account/src1 container@account/src2 container@account/dst
rm {container1,container2}@account/src
```

Please not for shorthand version additional [configuration](#configuration) required.
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

The software will prompt a secret for the path unless it specified 
in *known-secrets* section