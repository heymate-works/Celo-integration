# Celo SDK
An easy to use and understand SDK for integrating Android apps with Celo block-chain.
All the methods inside the SDK are thread safe. Internally a single thread is used to handle communications with the block-chain. The callback methods are called within the main thread.

*The app module is not functional. It has been used by the developers to understand Celo and develop the SDK.*

## Working with accounts
```java
// First time creating an account
CeloAccount celoAccount = CeloAccount.randomAccount();

// These are the keys
String privateKey = celoAccount.privateKey;
String publicKey = celoAccount.publicKey;

// Recreate the account instance
celoAccount = new CeloAccount(privateKey, publicKey);

// Simply get the mnemonic
String mnemonic = celoAccount.getMnemonic();

// Get private key from mnemonic
celoAccount = CeloAccount.fromMnemonic(mnemonic);
```

## Communicating with Celo
```java
// You wanna keep it as a singleton
CeloSDK celoSDK = new CeloSDK(context.getApplicationContext(), CeloContext.ALFAJORES, account);

// What is my address?
celoSDK.getAddress((success, address, errorCause) -> { String myAddress = address; });

// Want address for a phone number?
celoSDK.lookupPhoneNumber(phoneNumber, (success, assignedAccounts, errorCause) -> {
    List<String> addressesAssociatedWithThePhoneNumber = assignedAccounts;
});

// Is this phone number assigned to me? (because you should always check. User can unassigned.)
celoSDK.lookupPhoneNumberOwnership(phoneNumber, (success, verified, completedAttestations, totalAttestations, remainingAttestations, errorCause) -> {
    boolean attestationNotStartedOrNotCompleted = !verified;
});

// Wanna assign a phone number to this account? Or is it not completed yet?
celoSDK.requestAttestationsForPhoneNumber(phoneNumber, (countsAreReliable, newAttestations, totalAttestations, completedAttestations, errorCause) -> {
    int howManyNewSMSsToWaitFor = newAttestations;
});

// The user has received the code and want to complete the attestation? (the code can be a URL or an 8-digit number)
celoSDK.completeAttestationForPhoneNumber(phoneNumber, code, (verified, completed, total, remaining, errorCause) -> {
    boolean happy = verified;
});

// Want to query the account's balance?
celoSDK.getBalance((success, rawCUSD, rawGold, cUSDCents, gold, errorCause) -> {
    String beautifulCUSD = "$" + (cUSDCents / 100) + "." + (cUSDCents % 100);
});
```

Other functionality will hopefully be added sometime in the future.
