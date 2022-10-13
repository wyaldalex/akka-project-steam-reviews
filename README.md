# Steam Reviews

#### Dataset from [Kaggle: Steam Reviews 2021 by Marko M.](https://www.kaggle.com/datasets/najzeko/steam-reviews-2021)

Dataset of around 21 million user reviews of around 300 different games on Steam. Obtained using Steam's provided API outlined
in the Steamworks documentation: https://partner.steamgames.com/doc/store/getreviews

## Dataset Entities

In total three different entities can be modeled from the dataset: Game, User and Review.

The contents of each one of those entities are available [in this diagram](src/main/resources/Steam%20Reviews.drawio)

## System Requirements

- sbt 1.7.1
- Java 8
- Scala 2.13.8
- Docker 20 or higher

## Instructions to set up the application

1. Run this command to set up the Cassandra Docker container:

    ```shell
    docker-compose up
    ```

2. To install the dependencies use sbt: ``sbt compile`` or enter the sbt console with ``sbt`` and then ``compile``

## Running the application

To run the application use ``sbt run`` or inside the sbt console with ``run``,
you'll be asked to choose between the two different main entry points: ``FileLoadApp`` and ``HttpApp``

To load the CSV data use ``FileLoadApp``
To start the HTTP server use ``HttpApp``

## API Docs

You can check the API Docs in a Postman Collection:

[![Run in Postman](https://run.pstmn.io/button.svg)](https://god.gw.postman.com/run-collection/14357878-4809d6b9-cbc1-42bb-9a30-99f9dc126d00?action=collection%2Ffork&collection-url=entityId%3D14357878-4809d6b9-cbc1-42bb-9a30-99f9dc126d00%26entityType%3Dcollection%26workspaceId%3Db94a4f7a-c3e6-4335-81b4-aca8489e6039)
        
