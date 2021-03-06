Scorex-Basics Sub-Module
========================

The module contains utility functions & basic common structure to be used by other parts of the project:
 
- Block structure and corresponding structures. Please note, there's no need to inherit from the Block trait, all 
 functions to generate / parse / check a block are already in the trait & Block companion object
- ConsensusModule interface to a consensus module(see "Consensus Module" section below) 
- TransactionModule interface to a transaction module(see "Transaction Module" section below) 
- Interfaces for state & history.
- Basic transaction interface 
- Cryptographic hash functions. The only implementation in use at the moment is SHA-256
- Signing/verification functions. Curve 25519 is the only current implementation 
- RipeMD160 / Base58
- Accounts. Basically an account is just a wrapper around valid address provided as string, could be 
accomplished with public key or public/private keypair. 
- NTP client for time synchronization(but please note, there's no global time in a cryptocurrency p2p
network!)
- ScorexLogging trait to be mixed into classes for logging  


Block
-----
A block is a an atomic piece of data network participates are agreed on.
 
  A block has:
  
  - transactions data: a sequence of transactions, where a transaction is an atomic state update.
  Some metadata is possible as well(transactions Merkle tree root, state Merkle tree root etc)
 
  - consensus data to check whether a block was generated by a right party in a right way. E.g.
  "baseTarget" & "generatorSignature" fields in the Nxt block structure, nonce & difficulty in the
  Bitcoin block structure.
 
  - a signature(s) of a block generator(s)
 
  - additional data: block structure version no, timestamp etc

State & History
---------------


Transaction Module
------------------


Consensus Module
----------------


TODO:
-----

Other functions can be added to build offchain/onchain protocols e.g. other hash 
functions(e.g. keccak256 / sha3), Merkle trees, one-way accumulators, commitments (e.g. Pedersen commitment), 
ring signatures etc. 