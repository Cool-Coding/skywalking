# Compatibility

SkyWalking 8.0+ uses v3 protocols. Agents don't have to keep the identical versions as the OAP backend.

## SkyWalking Native Agents

| OAP Server Version | Java          | Python    | NodeJS    | LUA | Kong | Browser Agent | Rust | Rover(ebpf agnet) | Satellite  |
|--------------------|---------------|-----------|-----------|-----|------|---------------|------|-------------------|------------|
| 8.0.1 - 8.1.0      | 8.0.0 - 8.3.0 | < = 0.6.0 | < = 0.3.0 | All | All  | No            | All  | No                | No         |
| 8.2.0 - 8.3.0      | 8.0.0 - 8.3.0 | < = 0.6.0 | < = 0.3.0 | All | All  | All           | All  | No                | No         |
| 8.4.0 - 8.8.1      | \> = 8.0.0    | All       | All       | All | All  | All           | All  | No                | No         |
| 8.9.0+             | \> = 8.0.0    | All       | All       | All | All  | All           | All  | No                | \> = 0.4.0 |
| 9.0.0              | \> = 8.0.0    | All       | All       | All | All  | All           | All  | No                | \> = 0.4.0 |
| 9.1.0+             | \> = 8.0.0    | All       | All       | All | All  | All           | All  | \> = 0.1.0        | \> = 1.0.0 |

## Ecosystem Agents

All following agent implementations are a part of the SkyWalking ecosystem. All the source codes and their distributions
don't belong to the Apache Software Foundation.

| OAP Server Version | DotNet        | Go2sky        | cpp2sky   | PHP agent  |
|--------------------|---------------|---------------|-----------|------------|
| 8.0.1 - 8.3.0      | 1.0.0 - 1.3.0 | 0.4.0 - 0.6.0 | < = 0.2.0 | \> = 3.0.0 |
| 8.4.0+             | \> = 1.0.0    | \> = 0.4.0    | All       | \> = 3.0.0 |
| 9.0.0+             | \> = 1.0.0    | \> = 0.4.0    | All       | \> = 3.0.0 |

All these projects are maintained by their own communities, and please reach them if you face any compatibility issues.

___
All above compatibility are only references, and if you face an `unimplemented` error, it means you need to upgrade the
OAP backend to support newer features in the agents.
