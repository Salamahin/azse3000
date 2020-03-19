# AZSE3000
This is surely not an Azure Storage Explorer (3000)

Provides bash-like syntax to operate on top of BlockBlobs in Azure

## Supported syntax
```
cp from1 from2 from3 to
mv from1 from2 from3 to
rm from1 from2
cp from1 to1 && mv from2 to2 && rm from3
```
 
Every path within a single command is processed in parallel mode, while execution 
of commands separated by *&&* is sequential
 
## Path format
Default format is *https://account.blob.core.windows.net/container/path*

## secrets.conf
Optional file containing sensitive information
```conf
parallelism: 200

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