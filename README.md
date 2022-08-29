# Atlas Ride Scheduler Solution

## How to run the solution

- Build the docker image:
```docker build -f Dockerfile -t atlas:latest .```


- Run docker compose: ```docker-compose up```

## Accessing output files

Two csv files are generated after running the application: `stop_details_enriched.csv`  and `stop_details_flattened.csv`

Both files are mounted in a docker volume directory `atlasridescheduler_out`

Run ```docker volume inspect atlasridescheduler_atlas``` to check the Mountpoint